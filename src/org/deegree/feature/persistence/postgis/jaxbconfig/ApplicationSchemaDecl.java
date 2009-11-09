//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-792 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2009.11.09 at 03:42:05 PM MEZ 
//


package org.deegree.feature.persistence.postgis.jaxbconfig;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * Root element (container for all feature type
 *         declarations)
 * 
 * <p>Java class for ApplicationSchema element declaration.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;element name="ApplicationSchema">
 *   &lt;complexType>
 *     &lt;complexContent>
 *       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *         &lt;sequence>
 *           &lt;element name="Mapping">
 *             &lt;complexType>
 *               &lt;complexContent>
 *                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                   &lt;sequence>
 *                     &lt;element name="JDBCConnId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *                     &lt;element name="UseObjectLookupTable" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *                   &lt;/sequence>
 *                 &lt;/restriction>
 *               &lt;/complexContent>
 *             &lt;/complexType>
 *           &lt;/element>
 *           &lt;element ref="{http://www.deegree.org/feature/featuretype}FeatureType" maxOccurs="unbounded"/>
 *         &lt;/sequence>
 *         &lt;attribute name="targetNamespace" type="{http://www.w3.org/2001/XMLSchema}anyURI" />
 *       &lt;/restriction>
 *     &lt;/complexContent>
 *   &lt;/complexType>
 * &lt;/element>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "mapping",
    "featureType"
})
@XmlRootElement(name = "ApplicationSchema")
public class ApplicationSchemaDecl {

    @XmlElement(name = "Mapping", required = true)
    protected ApplicationSchemaDecl.Mapping mapping;
    @XmlElement(name = "FeatureType", required = true)
    protected List<FeatureTypeDecl> featureType;
    @XmlAttribute
    @XmlSchemaType(name = "anyURI")
    protected String targetNamespace;

    /**
     * Gets the value of the mapping property.
     * 
     * @return
     *     possible object is
     *     {@link ApplicationSchemaDecl.Mapping }
     *     
     */
    public ApplicationSchemaDecl.Mapping getMapping() {
        return mapping;
    }

    /**
     * Sets the value of the mapping property.
     * 
     * @param value
     *     allowed object is
     *     {@link ApplicationSchemaDecl.Mapping }
     *     
     */
    public void setMapping(ApplicationSchemaDecl.Mapping value) {
        this.mapping = value;
    }

    /**
     * Gets the value of the featureType property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the featureType property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFeatureType().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link FeatureTypeDecl }
     * 
     * 
     */
    public List<FeatureTypeDecl> getFeatureType() {
        if (featureType == null) {
            featureType = new ArrayList<FeatureTypeDecl>();
        }
        return this.featureType;
    }

    /**
     * Gets the value of the targetNamespace property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * Sets the value of the targetNamespace property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTargetNamespace(String value) {
        this.targetNamespace = value;
    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;sequence>
     *         &lt;element name="JDBCConnId" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *         &lt;element name="UseObjectLookupTable" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
     *       &lt;/sequence>
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "jdbcConnId",
        "useObjectLookupTable"
    })
    public static class Mapping {

        @XmlElement(name = "JDBCConnId", required = true)
        protected String jdbcConnId;
        @XmlElement(name = "UseObjectLookupTable")
        protected boolean useObjectLookupTable;

        /**
         * Gets the value of the jdbcConnId property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getJDBCConnId() {
            return jdbcConnId;
        }

        /**
         * Sets the value of the jdbcConnId property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setJDBCConnId(String value) {
            this.jdbcConnId = value;
        }

        /**
         * Gets the value of the useObjectLookupTable property.
         * 
         */
        public boolean isUseObjectLookupTable() {
            return useObjectLookupTable;
        }

        /**
         * Sets the value of the useObjectLookupTable property.
         * 
         */
        public void setUseObjectLookupTable(boolean value) {
            this.useObjectLookupTable = value;
        }

    }

}
